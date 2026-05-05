package com.learn.spring.mail.model;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

public final class Attachment {

    private final String name;
    private final Resource resource;
    private final String contentType;

    private Attachment(String name, Resource resource, String contentType) {
        Assert.hasText(name, "Attachment name must not be blank");
        Assert.notNull(resource, "Attachment resource must not be null");
        Assert.hasText(contentType, "Attachment contentType must not be blank");
        this.name = name;
        this.resource = resource;
        this.contentType = contentType;
    }

    public static Attachment of(String name, byte[] content, String contentType) {
        return new Attachment(name, new ByteArrayResource(content), contentType);
    }

    public static Attachment of(String name, Resource resource, String contentType) {
        return new Attachment(name, resource, contentType);
    }

    public static Attachment of(String name, InputStream stream, String contentType) {
        return new Attachment(name, new InputStreamResource(stream), contentType);
    }

    public static Attachment of(String name, File file) {
        String contentType;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (Exception e) {
            contentType = null;
        }
        return new Attachment(name, new FileSystemResource(file),
                contentType != null ? contentType : "application/octet-stream");
    }

    public String getName() { return name; }
    public Resource getResource() { return resource; }
    public String getContentType() { return contentType; }
}
