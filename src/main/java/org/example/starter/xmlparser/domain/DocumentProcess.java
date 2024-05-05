package org.example.starter.xmlparser.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.*;
import java.util.List;

@Data
@NoArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "document")
public class DocumentProcess {
    private String id;
    @XmlElementRef(name = "data")
    private List<DataField> dataList;
}
