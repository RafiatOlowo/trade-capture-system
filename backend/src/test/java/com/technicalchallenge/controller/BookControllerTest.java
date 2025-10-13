package com.technicalchallenge.controller;

import com.technicalchallenge.dto.BookDTO;
import com.technicalchallenge.mapper.BookMapper;
import com.technicalchallenge.model.Book;
import com.technicalchallenge.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(BookController.class)
public class BookControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @MockBean
    private BookMapper bookMapper;

    @BeforeEach
    public void setup() {

        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        // Set default authenticated user for all requests (GET/POST/PUT/DELETE)
        .defaultRequest(get("/").with(user("testUser").roles("RISK_MANAGER"))) 
        .build();

        Book book = new Book();
        book.setBookName("Book Name");
        book.setId(1L);
        book.setActive(true);
        book.setVersion(1);
        book.setCostCenter(null);


        BookDTO bookDTO = new BookDTO();
        bookDTO.setBookName("Book Name");
        bookDTO.setId(1L);
        bookDTO.setActive(true);
        bookDTO.setVersion(1);

        when(bookService.getAllBooks()).thenReturn(List.of(bookDTO));
        when(bookMapper.toDto(book)).thenReturn(bookDTO);
        when(bookMapper.toEntity(bookDTO)).thenReturn(book);

    }

    @Test
    void shouldReturnAllBooks() throws Exception {
        mockMvc.perform(get("/api/books")
                .with(csrf()))
                .andExpect(status().isOk());
    }
    // Add more tests for POST, PUT, DELETE as needed
}
