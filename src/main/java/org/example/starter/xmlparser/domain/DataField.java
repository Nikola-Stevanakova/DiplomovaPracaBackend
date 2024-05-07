package org.example.starter.xmlparser.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.*;
import java.util.List;

@Data
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "data")
public class DataField {
    private String id;
    @XmlAttribute(name = "type")
    private String tagName;
//    @XmlElementRef(name = "allowedNets")
//    private AllowedNets allowedNets;
}
