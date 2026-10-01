package com.devboard.config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class GithubMvcConfig implements WebMvcConfigurer {
    private final AsyncTaskExecutor executor;
    public GithubMvcConfig(@Qualifier("githubExecutor") java.util.concurrent.Executor executor) {
        this.executor = (AsyncTaskExecutor) executor;
    }
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor).setDefaultTimeout(60000);
    }
}
