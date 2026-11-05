package com.coolapp.medibook.clinic;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "holiday")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Holiday extends BaseEntity {
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String name;
}
