package com.technicalchallenge.repository;

import com.technicalchallenge.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByBookName(String bookName);

    
    // Checks if a Book exists by its ID and if its 'active' flag is true.
    boolean existsByIdAndActive(Long id, boolean active);
}
