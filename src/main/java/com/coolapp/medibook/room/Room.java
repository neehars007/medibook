package com.coolapp.medibook.room;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "room")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;
    @Column(nullable = false)
    private boolean active = true;
}
