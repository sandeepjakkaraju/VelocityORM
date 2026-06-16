package com.velocityorm.test.entity;

import com.velocityorm.core.annotation.*;

@Entity
@Table("users")
public class User {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    
    @Encrypted
    private String email;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
