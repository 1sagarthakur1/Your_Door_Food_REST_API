package com.masai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.masai.model.Category;
import com.masai.model.Item;
import com.masai.model.Restaurant;

@Repository
public interface ItemRepo extends JpaRepository<Item, Integer>{

	

}
