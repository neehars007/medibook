package com.coolapp.medibook.video;

import com.coolapp.medibook.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Jitsi Meet rooms (free, no API key). The room name is an unguessable random token; swap this
 * class for a Daily.co or Zoom client to get authenticated rooms.
 */
@Service
@RequiredArgsConstructor
public class VideoLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final AppProperties props;

    public String newMeetingLink() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return props.video().baseUrl() + "/MediBook-" + HexFormat.of().formatHex(b);
    }
}
