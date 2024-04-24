package org.example.starter.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xml.files")
public class XmlProperties {

    @Value("${xml.files.location.path}")
    private String filePath;
}
