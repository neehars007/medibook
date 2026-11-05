package com.coolapp.medibook.clinic;

import com.coolapp.medibook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "specialty")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
public class Specialty extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String description;
    private String icon;
}
