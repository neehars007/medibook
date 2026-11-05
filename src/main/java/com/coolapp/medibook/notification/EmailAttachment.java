package com.coolapp.medibook.notification;

public record EmailAttachment(String filename, byte[] bytes, String contentType) {}
