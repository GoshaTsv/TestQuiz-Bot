package org.example.bot;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.support.AbstractResourceBasedMessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Properties;

public class YamlMessageSource extends ReloadableResourceBundleMessageSource {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Override
    protected PropertiesHolder refreshProperties(String filename, PropertiesHolder propHolder) {
        Resource resource = resolver.getResource(filename + ".yaml");
        if (!resource.exists()) {
            return super.refreshProperties(filename, propHolder);
        }
        try {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(resource);
            factory.afterPropertiesSet();
            Properties props = factory.getObject();
            return new PropertiesHolder(props, resource.lastModified());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load YAML message source: " + filename, e);
        }
    }
}