package br.com.manager.models.dto;

import org.springframework.core.io.Resource;

public record LocalImageDto(Resource resource, String etag, boolean local) {}
