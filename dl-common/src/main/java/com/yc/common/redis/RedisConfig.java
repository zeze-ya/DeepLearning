package com.yc.common.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 功能描述:Redis配置类
 * 自定义RedisTemplate
 *
 * @EnableCaching: 开启基于注解的缓存
 * @Author: xieyc && 紫色年华
 * @Date: 2019-06-12
 * @Version: 1.0.0
 */
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport {

    /**
     * @return 自定义策略生成的key
     * @description 自定义的缓存key的生成策略 若想使用这个key
     * 只需要将注解上keyGenerator的值设置为keyGenerator即可
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {
            @Override
            public Object generate(Object target, Method method, Object... params) {
                StringBuffer sb = new StringBuffer();
                sb.append(target.getClass().getName());
                sb.append(method.getName());
                for (Object obj : params) {
                    sb.append(obj.toString());
                }
                return sb.toString();
            }
        };
    }

    /**
     * 配置redisTemplate
     *
     * @param factory
     * @return
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // 连接工厂
        redisTemplate.setConnectionFactory(factory);
        // 使用Jackson2JsonRedisSerializer来序列化和反序列化redis的value值(默认使用JDK的序列化方式)
        Jackson2JsonRedisSerializer<Object> jacksonSerial = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        // 指定要序列化的域,field,get和set,以及修饰符范围，ANY是都有包括private和public
        om.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
        // 指定序列化输入的类型，类必须是非final修饰的，final修饰的类，比如String,Integer等会抛出异常
        om.enableDefaultTyping(DefaultTyping.NON_FINAL);
        // 解决jackson2无法反序列化LocalDateTime的问题
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.registerModule(new JavaTimeModule());
        jacksonSerial.setObjectMapper(om);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // value 值采用json序列化
        redisTemplate.setValueSerializer(jacksonSerial);
        // 设置hash key 和 value序列化模式
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(jacksonSerial);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 缓存配置管理器(选择redis作为默认缓存工具)
     */
    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory factory) {
        // 以锁写入的方式创建RedisCacheWriter对象
        RedisCacheWriter writer = RedisCacheWriter.lockingRedisCacheWriter(factory);
        // 设置缓存有效期一小时
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1)); // 设置缓存有效期一小时;
        return new RedisCacheManager(writer, config);
    }

}
