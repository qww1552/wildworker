package com.a304.wildworker.domain.title;

import com.a304.wildworker.domain.common.BaseEntity;
import com.a304.wildworker.domain.user.User;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "title_awarded",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_title_awarded_user_id_title_id", columnNames = {
                        "user_id",
                        "title_id"})})
public class TitleAwarded extends BaseEntity {

    @JoinColumn(nullable = false)
    @ManyToOne(targetEntity = User.class, fetch = FetchType.LAZY)
    private User user;

    @JoinColumn(nullable = false)
    @ManyToOne(targetEntity = Title.class, fetch = FetchType.LAZY)
    private Title title;

}
