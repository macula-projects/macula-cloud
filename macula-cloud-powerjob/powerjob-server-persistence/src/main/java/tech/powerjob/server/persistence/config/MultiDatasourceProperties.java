package tech.powerjob.server.persistence.config;

import com.google.common.collect.Maps;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多重数据源配置
 *
 * @author Kung Yao
 * @since 2020/4/27
 */
@Component
@ConfigurationProperties("spring.datasource")
public class MultiDatasourceProperties {

    private DataSourceProperties remote = new DataSourceProperties();

    private DataSourceProperties local = new DataSourceProperties();

    public DataSourceProperties getLocal() {
        return local;
    }

    public void setLocal(DataSourceProperties local) {
        this.local = local;
    }

    public DataSourceProperties getRemote() {
        return remote;
    }

    public void setRemote(DataSourceProperties remote) {
        this.remote = remote;
    }

    public static class DataSourceProperties {

        private HibernateProperties hibernate = new HibernateProperties();

        public HibernateProperties getHibernate() {
            return hibernate;
        }

        public void setHibernate(HibernateProperties hibernate) {
            this.hibernate = hibernate;
        }
    }

    public static class HibernateProperties {

        private Map<String, String> properties = Maps.newHashMap();

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }
    }
}
