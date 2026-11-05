package com.coolapp.medibook.notification;

import java.util.List;

/** Rendered by templates/email/notification.html. Never put diagnoses or medication here. */
public record EmailContent(String title, String greeting, List<String> lines, String ctaLabel, String ctaUrl) {}
