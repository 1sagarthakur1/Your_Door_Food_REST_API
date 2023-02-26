package com.masai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.masai.exception.CategoryException;
import com.masai.model.Item;
import com.masai.service.CategoryService;

@RestController
@RequestMapping(value = "/yourDoorFood")
public class CategoryController {

	@Autowired
	private CategoryService iCategoryService;
	
	@GetMapping("/categories/{cateName}")
	public ResponseEntity<List<Item>> getItemsByCategoryName(@PathVariable("cateName") String categoryName) throws CategoryException {
		return new ResponseEntity<>(iCategoryService.getItemsByCategoryName(categoryName),HttpStatus.FOUND);
		
	}
	
	
	 
	 
}
