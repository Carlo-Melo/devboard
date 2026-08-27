package com.devboard.ratelimit;

import com.devboard.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Before(value = "@annotation(rateLimit)", argNames = "joinPoint,rateLimit")
    public void enforce(JoinPoint joinPoint, RateLimit rateLimit) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String resolvedKey = method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                + ":" + evaluateKey(method, joinPoint.getArgs(), rateLimit.key());

        boolean allowed = rateLimiterService.tryConsume(resolvedKey, rateLimit.limit(), rateLimit.window());
        if (!allowed) {
            long retryAfter = rateLimiterService.remainingSeconds(resolvedKey);
            throw new RateLimitExceededException("Limite de requisições excedido. Tente novamente mais tarde.", retryAfter);
        }
    }

    private String evaluateKey(Method method, Object[] args, String expression) {
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return parser.parseExpression(expression).getValue(context, String.class);
    }
}
