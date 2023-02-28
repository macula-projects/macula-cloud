package dev.macula.cloud.system.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "system.tenant.table")
@Component
@Deprecated
@Data
public class CustomTenantProperties {
    private Set<String> containsTenantTables = new HashSet<>();

    public boolean contains(String tableName){
        return containsTenantTables.contains(tableName);
    }
}
