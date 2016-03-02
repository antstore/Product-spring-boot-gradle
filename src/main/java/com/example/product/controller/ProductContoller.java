package com.example.product.controller;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.error.DocumentAlreadyExistsException;
import com.couchbase.client.java.view.ViewQuery;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import com.example.product.entity.Product;
import com.example.product.validate.ProductValidator;
import com.google.gson.reflect.TypeToken;

@RestController
@RequestMapping("/product")
public class ProductContoller {

	private final Bucket bucket;

	@Autowired
	public ProductContoller(final Bucket bucket) {
		this.bucket = bucket;
	}

	@RequestMapping("/allproducts")
	@ResponseBody
	public ResponseEntity<String> getAllProducts(@RequestParam(required = false) Integer offset,
			@RequestParam(required = false) Integer limit) throws JsonParseException, JsonMappingException, IOException {
		ViewQuery query = ViewQuery.from("product_id", "by_id");
		if (limit != null && limit > 0) {
			query.limit(limit);
		}
		if (offset != null && offset > 0) {
			query.skip(offset);
		}
		ViewResult result = bucket.query(query);
		if (!result.success()) {
			return new ResponseEntity<String>(result.error().toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		} else {
			ObjectMapper objectMapper = new ObjectMapper();
			List<Product> listTitle = getAttributeList(result);
			return new ResponseEntity<String>(objectMapper.writeValueAsString(listTitle), HttpStatus.OK);
		}
	}

	@RequestMapping(method = RequestMethod.GET, value = "/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public ResponseEntity<String> getProduct(@PathVariable("id") String id) {
		JsonDocument doc = bucket.get(id);
		if (doc != null) {
			return new ResponseEntity<String>(doc.content().toString(), HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}
	}

	@RequestMapping(method = RequestMethod.DELETE, value = "/delete/{id}")
	@ResponseBody
	public ResponseEntity<String> deleteProduct(@PathVariable("id") String prodId) {
		JsonDocument deleted = bucket.remove(prodId);
		return new ResponseEntity<String>("" + deleted.cas(), HttpStatus.OK);
	}

	@RequestMapping(method = RequestMethod.POST, value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> createProduct(@RequestBody Product inputObject) {
		String id = "";
		try {
			if (new ProductValidator().validte(inputObject)) {
				JsonObject prod = productToJsonObjConverter(inputObject);
				bucket.insert(JsonDocument.create(inputObject.getId().toString(), prod));
				return new ResponseEntity<String>(inputObject.getId().toString(), HttpStatus.CREATED);
			}
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		} catch (DocumentAlreadyExistsException e) {
			return new ResponseEntity<String>("Id " + id + " already exist", HttpStatus.CONFLICT);
		} catch (Exception e) {
			return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		return null;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/searchtitle/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> searchProductByTitleToken(@PathVariable final String token)
			throws JsonParseException, JsonMappingException, IOException {
		ViewQuery query = ViewQuery.from("products", "by_title");
		List<Product> listTitle = new ArrayList<Product>();
		ViewResult result = bucket.query(query);
		if (!result.success()) {
			return new ResponseEntity<String>(result.error().toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		} else {
			listTitle = getAttributeList(result);
			Type type = new TypeToken<List<Product>>() {
			}.getType();
			List<Product> resul = listTitle.stream().filter(P -> P.getTitle().contains(token))
					.collect(Collectors.toList());
			return new ResponseEntity<String>(resul.toString(), HttpStatus.OK);
		}
	}

	@RequestMapping(method = RequestMethod.PUT, value = "/update", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<String> updateProduct(@RequestBody Product inputObject) {
		JsonDocument doc = bucket.get(inputObject.getId().toString());
		if (doc != null) {
			JsonObject prod = productToJsonObjConverter(inputObject);
			bucket.upsert(JsonDocument.create(inputObject.getId().toString(), prod));
			return new ResponseEntity<String>(inputObject.getId().toString(), HttpStatus.OK);
		} else {
			return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
		}

	}

	private JsonObject productToJsonObjConverter(Product product) {
		JsonObject prod = JsonObject.create();
		prod.put("id", product.getId());
		prod.put("title", product.getTitle());
		prod.put("description", product.getDescription());
		prod.put("primaryImage", product.getPrimaryImage());
		prod.put("category", product.getCategory());
		prod.put("color", product.getColor());
		prod.put("size", product.getSize());
		return prod;
	}
	
	private List<Product> getAttributeList(ViewResult result) throws JsonParseException, JsonMappingException, IOException{
		List<Product> listTitle = new ArrayList<Product>();
		ObjectMapper mapper = new ObjectMapper();
		List<ViewRow> listRows = result.allRows();
		
		listRows.forEach(name -> {
				Product prod = null;
				try {
					prod = mapper.readValue(name.value().toString(), Product.class);
				} catch (Exception e) {
					e.printStackTrace();
				}
			
			listTitle.add(prod);
		});
		return listTitle;
	}
	

	@RequestMapping(method = RequestMethod.GET, value = "attribute/{attribute}/{property}", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> fileterAttribute(@PathVariable final String attribute,
			@PathVariable final String property) throws JsonParseException, JsonMappingException, IOException {
		ViewQuery query = ViewQuery.from("product_id", "by_id");
		List<Product> listTitle = new ArrayList<Product>();
		ViewResult result = bucket.query(query);
		if (!result.success()) {
			return new ResponseEntity<String>(result.error().toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		} else {
			if (attribute.equalsIgnoreCase("color")) {
				listTitle = getAttributeList(result);
				
				List<Product> resul = listTitle.stream().filter(P -> P.getColor().equals(property))
						.collect(Collectors.toList());
				return new ResponseEntity<String>(resul.toString(), HttpStatus.OK);
			}else if(attribute.equalsIgnoreCase("size")){
				listTitle = getAttributeList(result);
				
				List<Product> resul = listTitle.stream().filter(P ->  P.getSize()==Integer.parseInt(property))
						.collect(Collectors.toList());
				return new ResponseEntity<String>(resul.toString(), HttpStatus.OK);
			}
			return new ResponseEntity<String>(" choose between:color,size or Inputerror", HttpStatus.METHOD_NOT_ALLOWED);
		}
	}

}
