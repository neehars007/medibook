package com.coolapp.medibook.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queue;

    /** Public TV page polls this ("Now serving: Token 12 - Dr. Mehta - Room 2"). */
    @GetMapping("/display")
    public List<QueueService.DoctorQueue> display() {
        return queue.display();
    }
}
