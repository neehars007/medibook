package com.coolapp.medibook.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RoomDto(UUID id, @NotBlank String name, @NotNull RoomType type, boolean active) {}
