package com.likelionsns.final_project.repository;

import com.likelionsns.final_project.domain.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PostRepository extends JpaRepository<Post, Integer> {
    Page<Post> findAllByUserId(Pageable pageable, Integer userId);
}
