package org.example.classes.appLinking;

public class Image {
    private String dataURL;
    private String name;
    private String size;
    private String type;

    public Image(String dataURL, String name, String size, String type) {
        this.dataURL = dataURL;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public String getDataURL() {
        return dataURL;
    }

    public void setDataURL(String dataURL) {
        this.dataURL = dataURL;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
