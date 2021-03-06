package com.weweibuy.framework.rocketmq.core.consumer;

import com.weweibuy.framework.rocketmq.annotation.BatchHandlerModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.TypeUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;

/**
 * @author durenhao
 * @date 2020/1/6 23:14
 **/
@Slf4j
public class RocketHandlerMethod {

    private HandlerMethodArgumentResolverComposite argumentResolverComposite;

    private Object bean;

    private Method bridgedMethod;

    private MethodParameter[] methodParameters;

    private Integer batchMaxSize;

    private BatchHandlerModel batchHandlerModel;

    public RocketHandlerMethod(MethodRocketListenerEndpoint endpoint) {
        this.bean = endpoint.getBean();
        this.bridgedMethod = BridgeMethodResolver.findBridgedMethod(endpoint.getMethod());
        this.argumentResolverComposite = endpoint.getArgumentResolverComposite();
        this.batchMaxSize = endpoint.getConsumeMessageBatchMaxSize();
        this.batchHandlerModel = endpoint.getBatchHandlerModel();
        this.methodParameters = initMethodParameters();
    }


    /**
     * 解析方法参数
     *
     * @param message
     * @param providedArgs
     * @return
     */
    private Object[] getMethodArgumentValues(Object message, Object... providedArgs) {
        Object[] args = new Object[methodParameters.length];
        for (int i = 0; i < methodParameters.length; i++) {
            MethodParameter methodParameter = methodParameters[i];

            args[i] = findProvidedArgument(methodParameter, message, providedArgs);

            if (args[i] != null) {
                continue;
            }

            HandlerMethodArgumentResolver argumentResolver = argumentResolverComposite.getArgumentResolver(methodParameter);

            if (argumentResolver == null) {
                log.warn("Method: {}, 第: {} 个参数, 无法找到匹配的参数处理器", methodParameter.getMethod(), i);
                throw new IllegalStateException("Method: " + methodParameter.getMethod() + ", 第: " + i + " 个参数, 无法找到匹配的参数处理器");
            }
            // 解析参数
            args[i] = argumentResolver.resolveArgument(methodParameter, message);

        }

        return args;
    }


    protected Object findProvidedArgument(MethodParameter parameter, Object message, Object... providedArgs) {

        Class<?> parameterType = parameter.getParameterType();

        // 单个消费 参数类型为 MessageExt
        if ((batchMaxSize == 1 || BatchHandlerModel.FOREACH.equals(batchHandlerModel))
                && parameterType.isInstance(message)) {
            return message;
        }

        Type type = parameter.getNestedGenericParameterType();

        // 参数为List<Message>
        if (isListMessage(type)) {
            if (message instanceof Collection) {
                return message;
            }
            return Collections.unmodifiableList(Collections.singletonList(message));
        }

        if (!ObjectUtils.isEmpty(providedArgs)) {
            for (Object providedArg : providedArgs) {
                if (parameterType.isInstance(providedArg)) {
                    return providedArg;
                }
            }
        }
        return null;
    }


    /**
     * 调用目标方法
     *
     * @param args
     * @return
     */
    private Object doInvoke(Object[] args) throws Exception {
        ReflectionUtils.makeAccessible(bridgedMethod);
        try {
            return bridgedMethod.invoke(bean, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof Exception) {
                throw (Exception) targetException;
            } else if (targetException instanceof Error) {
                throw (Error) targetException;
            } else {
                throw new IllegalStateException(targetException);
            }
        }
    }


    public Object invoke(Object message, Object... providedArgs) throws Exception {

        Object[] args = getMethodArgumentValues(message, providedArgs);
        return doInvoke(args);
    }

    private MethodParameter[] initMethodParameters() {
        int count = this.bridgedMethod.getParameterCount();
        MethodParameter[] result = new MethodParameter[count];
        for (int i = 0; i < count; i++) {
            SynthesizingMethodParameter parameter = new RocketMethodParameter(this.bridgedMethod, i, batchMaxSize);
            result[i] = parameter;
        }
        return result;
    }


    private boolean isListMessage(Type type) {
        boolean assignable = TypeUtils.isAssignable(Collection.class, type);
        if (assignable && type instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
            return actualTypeArguments.length > 0 && TypeUtils.isAssignable(Message.class, actualTypeArguments[0]);
        }
        return false;
    }

}
