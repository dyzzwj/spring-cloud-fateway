package org.springframework.cloud.gateway.filter.ratelimit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.constraints.Min;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

/**
 * See https://stripe.com/blog/rate-limiters and
 * https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d#file-1-check_request_rate_limiter-rb-L11-L34
 *
 * @author Spencer Gibb
 * @author Ronny Bräunlich
 */
@ConfigurationProperties("spring.cloud.gateway.redis-rate-limiter")
public class RedisRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config> implements ApplicationContextAware {
	@Deprecated
	public static final String REPLENISH_RATE_KEY = "replenishRate";
	@Deprecated
	public static final String BURST_CAPACITY_KEY = "burstCapacity";

	public static final String CONFIGURATION_PROPERTY_NAME = "redis-rate-limiter";
	public static final String REDIS_SCRIPT_NAME = "redisRequestRateLimiterScript";
	public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
	public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";
	public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";

	private Log log = LogFactory.getLog(getClass());

	private ReactiveRedisTemplate<String, String> redisTemplate;
	private RedisScript<List<Long>> script;
	private AtomicBoolean initialized = new AtomicBoolean(false);
	private Config defaultConfig;

	// configuration properties
	/** Whether or not to include headers containing rate limiter information, defaults to true. */
	private boolean includeHeaders = true;

	/** The name of the header that returns number of remaining requests during the current second. */
	private String remainingHeader = REMAINING_HEADER;

	/** The name of the header that returns the replenish rate configuration. */
	private String replenishRateHeader = REPLENISH_RATE_HEADER;

	/** The name of the header that returns the burst capacity configuration. */
	private String burstCapacityHeader = BURST_CAPACITY_HEADER;

	public RedisRateLimiter(ReactiveRedisTemplate<String, String> redisTemplate,
			RedisScript<List<Long>> script, Validator validator) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, validator);
		this.redisTemplate = redisTemplate;
		this.script = script;
		initialized.compareAndSet(false, true);
	}

	public RedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, null);
		this.defaultConfig = new Config()
				.setReplenishRate(defaultReplenishRate)
				.setBurstCapacity(defaultBurstCapacity);
	}

	public boolean isIncludeHeaders() {
		return includeHeaders;
	}

	public void setIncludeHeaders(boolean includeHeaders) {
		this.includeHeaders = includeHeaders;
	}

	public String getRemainingHeader() {
		return remainingHeader;
	}

	public void setRemainingHeader(String remainingHeader) {
		this.remainingHeader = remainingHeader;
	}

	public String getReplenishRateHeader() {
		return replenishRateHeader;
	}

	public void setReplenishRateHeader(String replenishRateHeader) {
		this.replenishRateHeader = replenishRateHeader;
	}

	public String getBurstCapacityHeader() {
		return burstCapacityHeader;
	}

	public void setBurstCapacityHeader(String burstCapacityHeader) {
		this.burstCapacityHeader = burstCapacityHeader;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		if (initialized.compareAndSet(false, true)) {
			this.redisTemplate = context.getBean("stringReactiveRedisTemplate", ReactiveRedisTemplate.class);
			this.script = context.getBean(REDIS_SCRIPT_NAME, RedisScript.class);
			if (context.getBeanNamesForType(Validator.class).length > 0) {
				this.setValidator(context.getBean(Validator.class));
			}
		}
	}

	/* for testing */ Config getDefaultConfig() {
		return defaultConfig;
	}

	/**
	 * This uses a basic token bucket algorithm and relies on the fact that Redis scripts
	 * execute atomically. No other operations can run between fetching the count and
	 * writing the new count.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Mono<Response> isAllowed(String routeId, String id) {
		if (!this.initialized.get()) {
			throw new IllegalStateException("RedisRateLimiter is not initialized");
		}

		Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);

		if (routeConfig == null) {
			throw new IllegalArgumentException("No Configuration found for route " + routeId);
		}

		// How many requests per second do you want a user to be allowed to do?
		//令牌桶每秒填充平均速率
		int replenishRate = routeConfig.getReplenishRate();

		// How much bursting do you want to allow?
		//令牌桶容量
		int burstCapacity = routeConfig.getBurstCapacity();

		try {
			/**
			 * 准备redis的key
			 */
			List<String> keys = getKeys(id);
			// The arguments to the LUA script. time() returns unixtime in seconds.
			//得到lua脚本参数
			List<String> scriptArgs = Arrays.asList(replenishRate + "", burstCapacity + "",
					Instant.now().getEpochSecond() + "", "1");
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)

			/**
			 * 执行redis lua脚本 获取令牌
			 * 返回结果为 [是否获取令牌成功, 剩余令牌数] ，其中，1 代表获取令牌成功，0 代表令牌获取失败
			 */
			Flux<List<Long>> flux = this.redisTemplate.execute(this.script, keys, scriptArgs);
			// .log("redisratelimiter", Level.FINER);
			/**
			 * 当 Redis Lua 脚本过程中发生异常，忽略异常，返回 Flux.just(Arrays.asList(1L, -1L)) ，即认为获取令牌成功。
			 * 为什么？在 Redis 发生故障时，我们不希望限流器对 Reids 是强依赖，并且 Redis 发生故障的概率本身就很低。
			 */
			return flux.onErrorResume(throwable -> Flux.just(Arrays.asList(1L, -1L)))
					/**
					 * Flux#reduce(A, BiFunction<A, ? super T, A>) 方法，将 Flux<List<Long>> 转换成 Mono<List<Long>> 。
					 * 因为 ReactiveRedisTemplate#execute(RedisScript<T>, List<K>, List<?>) 方法的执行结果为 Flux (多次 )，
					 * 实际在当前场景里，自行 Redis Lua 脚本只会返回一次数组，所以转换成 Mono (一次)
					 */
					.reduce(new ArrayList<Long>(), (longs, l) -> {
						longs.addAll(l);
						return longs;
					}).map(results -> {
						//返回结果为 [是否获取令牌成功, 剩余令牌数] ，其中，1 代表获取令牌成功，0 代表令牌获取失败
						boolean allowed = results.get(0) == 1L;
						Long tokensLeft = results.get(1);
						//返回响应
						Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));

						if (log.isDebugEnabled()) {
							log.debug("response: " + response);
						}
						return response;
					});
		}
		catch (Exception e) {
			/*
			 * We don't want a hard dependency on Redis to allow traffic. Make sure to set
			 * an alert so you know if this is happening too much. Stripe's observed
			 * failure rate is 0.01%.
			 */
			log.error("Error determining if user allowed from redis", e);
		}
		/**
		 * 发生异常时，例如 Redis 挂了，返回 Flux.just(Arrays.asList(1L, -1L)) ，即认为获取令牌成功。
		 */
		return Mono.just(new Response(true, getHeaders(routeConfig, -1L)));
	}

	@NotNull
	public Map<String, String> getHeaders(Config config, Long tokensLeft) {
		Map<String, String> headers = new HashMap<>();
		if (isIncludeHeaders()) {
			headers.put(this.remainingHeader, tokensLeft.toString());
			headers.put(this.replenishRateHeader, String.valueOf(config.getReplenishRate()));
			headers.put(this.burstCapacityHeader, String.valueOf(config.getBurstCapacity()));
		}
		return headers;
	}

	static List<String> getKeys(String id) {
		// use `{}` around keys to use Redis Key hash tags
		// this allows for using redis cluster

		// Make a unique key per user.
		//令牌桶前缀
		String prefix = "request_rate_limiter.{" + id;

		// You need two Redis keys for Token Bucket.
		/**
		 * request_rate_limiter.${id}.tokens:令牌桶剩余令牌数
		 */
		String tokenKey = prefix + "}.tokens";
		/**
		 * request_rate_limiter.${id}.timestamp:令牌桶最后填充令牌时间
		 */
		String timestampKey = prefix + "}.timestamp";
		return Arrays.asList(tokenKey, timestampKey);
	}

	@Validated
	public static class Config {
		@Min(1)
		private int replenishRate;

		@Min(1)
		private int burstCapacity = 1;

		public int getReplenishRate() {
			return replenishRate;
		}

		public Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		public int getBurstCapacity() {
			return burstCapacity;
		}

		public Config setBurstCapacity(int burstCapacity) {
			this.burstCapacity = burstCapacity;
			return this;
		}

		@Override
		public String toString() {
			return "Config{" +
					"replenishRate=" + replenishRate +
					", burstCapacity=" + burstCapacity +
					'}';
		}
	}
}
