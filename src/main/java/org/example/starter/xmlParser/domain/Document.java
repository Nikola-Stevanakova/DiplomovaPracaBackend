package org.example.starter.xmlParser.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.*;
import java.util.List;

@Data
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "document")
public class Document {
    private String id;
    private String initials;
    private String title;
    private boolean defaultRole;
    private boolean anonymousRole;
    private boolean transitionRole;
    @XmlElementRef(name = "data")
    private List<DataField> dataList;
}
