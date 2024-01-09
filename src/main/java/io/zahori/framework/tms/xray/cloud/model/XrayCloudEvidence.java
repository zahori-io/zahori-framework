package io.zahori.framework.tms.xray.cloud.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class XrayCloudEvidence {

    private String data;        // The attachment data encoded in base64
    private String filename;    // The file name for the attachment
    private String contentType; // The Content-Type representation header is used to indicate the original media type of the resource

    public XrayCloudEvidence() {
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

}
