package org.example.starter.xmlparser.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.*;

@Data
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "data")
public class DataField {
    private String id;
    private String type;
    private String title;
    private String init;
    @XmlAttribute(name = "type")
    private String tagName;
}
