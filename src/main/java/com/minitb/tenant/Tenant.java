package com.minitb.tenant;

import com.minitb.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "email")
	private String email;

	public Tenant(String title, String email) {
		this.title = title;
		this.email = email;
	}

}
