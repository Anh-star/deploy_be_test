package com.cmcu.itstudy.controller;

import com.cmcu.itstudy.dto.common.ApiResponse;
import com.cmcu.itstudy.dto.document.CategoryResponseDto;
import com.cmcu.itstudy.dto.document.TagResponseDto;
import com.cmcu.itstudy.entity.Category;
import com.cmcu.itstudy.entity.Tag;
import com.cmcu.itstudy.mapper.CategoryMapper;
import com.cmcu.itstudy.mapper.TagMapper;
import com.cmcu.itstudy.repository.CategoryRepository;
import com.cmcu.itstudy.repository.TagRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CategoryTagController {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public CategoryTagController(CategoryRepository categoryRepository,
                                 TagRepository tagRepository) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponseDto>>> getCategories() {
        List<Category> categories = categoryRepository.findAll();
        List<CategoryResponseDto> data = categories.stream()
                .map(CategoryMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, "Category list"));
    }

    @GetMapping("/tags/popular")
    public ResponseEntity<ApiResponse<List<TagResponseDto>>> getPopularTags() {
        List<Tag> tags = tagRepository.findTop15PopularTags(PageRequest.of(0, 15));
        List<TagResponseDto> data = tags.stream()
                .map(TagMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, "Popular tags"));
    }

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<List<TagResponseDto>>> getAllTags() {
        List<Tag> tags = tagRepository.findByActiveTrueOrderByNameAsc();
        List<TagResponseDto> data = tags.stream()
                .map(TagMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, "All active tags"));
    }
}

