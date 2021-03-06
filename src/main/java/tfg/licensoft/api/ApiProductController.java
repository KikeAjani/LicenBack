package tfg.licensoft.api;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;


import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.stripe.exception.StripeException;
import com.stripe.model.Plan;
import com.stripe.model.Sku;
import com.stripe.param.PlanCreateParams;

import tfg.licensoft.dtos.ProductDTO;
import tfg.licensoft.products.Product;
import tfg.licensoft.products.ProductService;
import tfg.licensoft.stripe.StripeServices;

@CrossOrigin
@RestController
@RequestMapping(value = "/api/products")
public class ApiProductController implements IProductController{

	@Autowired
	private ProductService productServ;
	
	@Autowired
	private StripeServices stripeServ;
	

    private ModelMapper modelMapper = new ModelMapper();
    
    private static final String CURRENCY = "currency";
    private static final String PRODUCT = "product";
    private static final String INTERVAL = "interval";
    private static final String NICKNAME = "nickname";
    private static final String AMOUNT = "amount";

	
	@GetMapping()
	public ResponseEntity<List<Product>> getProducts(HttpServletRequest req,@RequestParam Optional<String> search){
		if (!search.isPresent()) {
			return new ResponseEntity<>(productServ.findAllActives(), HttpStatus.OK);
		}else {
			return new ResponseEntity<>(this.productServ.findSearch(search.get()),HttpStatus.OK);
		}
		
	}

	@GetMapping("/{productName}") 
	public ResponseEntity<Product> getProduct(@PathVariable String productName) {
		Product p = this.productServ.findOne(productName);
		if (p != null) {
			return new ResponseEntity<>(p,HttpStatus.OK);
		}else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	} 
 
	
	

	@PostMapping("/")
	public ResponseEntity<Product> postProduct(@RequestBody ProductDTO product){
		Product savedProduct = this.convertToEntity(product);
		
		
		if (this.productServ.findOne(savedProduct.getName())==null) {
			int count=0;
			HashMap<String,String> plans = new HashMap<>();
			savedProduct.setPlans(plans);
			String productId="";
			Map<String, Object> params = new HashMap<>();
			com.stripe.model.Product productStripe;
			for (Map.Entry<String, Double> plan : savedProduct.getPlansPrices().entrySet()) {
				if(count==0) {
					if (plan.getKey().equals("L")) {
						params.put("name", savedProduct.getName());
						params.put("type", "good");
						params.put("shippable", false);
						params.put("url", savedProduct.getWebLink());
					}else {
						params.put("name", savedProduct.getName());
						params.put("type", "service");
					}
					try {
						productStripe =this.stripeServ.createProduct(params);
						productId = productStripe.getId();
						savedProduct.setProductStripeId(productId);
						savedProduct.setPhotoAvailable(false);
						savedProduct.setPhotoSrc("");
						
					} catch (StripeException e) {
						e.printStackTrace();
						return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

					}
				}
			    switch(plan.getKey()) {
				    case "L":
				    	this.createLproduct(savedProduct, plan.getValue(),productId);
				    	break;
				    
				    case "M":
				    	this.createMproduct(savedProduct, plan.getValue(),productId);
				    	break;
				    
				    case "D":
				    	this.createDproduct(savedProduct, plan.getValue(),productId);
				    	break;
				    
				    case "A":
				    	this.createAproduct(savedProduct, plan.getValue(), productId);
				    	break;
				    
				    case "MB":
				    	this.createMBproduct(savedProduct, plan.getValue(), productId);
				    	break;
				    default:
				    	break;
			    }
			    count++;
			}
			return new ResponseEntity<>(savedProduct,HttpStatus.OK);
		}else {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}
	}
	
	
	@PutMapping("/")
	public ResponseEntity<Product> editProduct(@RequestBody ProductDTO product){
		Product savedProduct = this.convertToEntity(product);
		
		Product p = this.productServ.findOne(savedProduct.getName());
		if (p==null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}else{
			p.setDescription(savedProduct.getDescription());
			try {
				com.stripe.model.Product pStripe = this.stripeServ.retrieveProduct(p.getProductStripeId());
				Map<String, Object> params = new HashMap<>();
				params.put("description", savedProduct.getDescription());

				this.stripeServ.updateProduct(pStripe, params);
			} catch (StripeException e) {
				e.printStackTrace();
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			} 
			p.setTrialDays(savedProduct.getTrialDays());
			p.setPhotoAvailable(savedProduct.isPhotoAvailable());
			p.setPhotoSrc(savedProduct.getPhotoSrc());
			p.setWebLink(savedProduct.getWebLink());
			Product newP = this.productServ.save(p);
			return new ResponseEntity<>(newP,HttpStatus.OK); 
		}	
	}
	
	@DeleteMapping("/{productName}")
	public ResponseEntity<Product> deleteProduct(@PathVariable String productName,HttpServletRequest request){
		Product p = this.productServ.findOne(productName);
		if(p==null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}else {
			try {
				com.stripe.model.Product product = this.stripeServ.retrieveProduct(p.getProductStripeId());
						Map<String, Object> params = new HashMap<>();
						params.put("active", false);
						this.stripeServ.updateProduct(product,params);
						p.setActive(false);
						p.setPhotoAvailable(false);
						this.productServ.save(p);
						return new ResponseEntity<>(p,HttpStatus.OK); 
			}catch(StripeException e) {
				e.printStackTrace();
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
			
		}

	}


	
	
	
	private void createLproduct(Product product, double price, String productId ) {
		try {
			Map<String, Object> inventory = new HashMap<>();
			inventory.put("type", "infinite");
			Map<String, Object> paramsSku = new HashMap<>();
			paramsSku.put("price",(int)(price*100) );  //Hay que ponerlo en centimos (y en entero)
			paramsSku.put("inventory", inventory);
			paramsSku.put(CURRENCY, "eur");
			paramsSku.put(PRODUCT,productId);
	 
			Sku sku = this.stripeServ.createSku(paramsSku);
			product.setSku(sku.getId());
			product.setProductStripeId(productId);
		}catch(StripeException e) {
			e.printStackTrace();
		}
		this.productServ.save(product);
	}
	 
	
	private void createMBproduct(Product product, double price, String productId ) {
		try {
			long l = (long) (price*100);
			PlanCreateParams params =
					  PlanCreateParams.builder()
					    .setCurrency("eur")
					    .setInterval(PlanCreateParams.Interval.MONTH)
					    .setProduct(productId)
					    .setNickname("MB")
					    .setAmount(l)
					    .setUsageType(PlanCreateParams.UsageType.METERED)
					    .build();

			Plan plan = this.stripeServ.createPlan(params);
			product.getPlans().put("MB",plan.getId());

		}catch(StripeException e) {
			e.printStackTrace();
		}
		this.productServ.save(product);
	}	
	
	private Map<String, Object> createParams(String productId, double price, String type) {
		
		Map<String, Object> params = new HashMap<>();
		params.put(CURRENCY, "eur");
		params.put(PRODUCT, productId);
		params.put(AMOUNT, (int)(price*100));
		params.put(NICKNAME, type);

		return params;
	}
	
	//private methods to create plans
	private void createMproduct(Product product, double price, String productId) {
		try {
			Map<String, Object> params = this.createParams(productId, price, "M");
			params.put(INTERVAL, "month");
			Plan plan1M = this.stripeServ.createPlan(params);
			product.getPlans().put("M",plan1M.getId());
			
		}catch(StripeException e) {
			e.printStackTrace();
		}
		this.productServ.save(product);
	}
	
	private void createAproduct(Product product, double price, String productId) {
		try {
			Map<String, Object> params = this.createParams(productId, price, "A");
			params.put(INTERVAL, "year");		
			Plan plan1A =this.stripeServ.createPlan(params);
			product.getPlans().put("A",plan1A.getId());
			
		}catch(StripeException e) {
			e.printStackTrace();
		}
		this.productServ.save(product);
	}
	
	private void createDproduct(Product product, double price, String productId) {
		try {
			Map<String, Object> params = this.createParams(productId, price, "D");
			
			params.put(INTERVAL, "day");
			
			Plan plan1D =this.stripeServ.createPlan(params);
			product.getPlans().put("D",plan1D.getId());
			
		}catch(StripeException e) {
			e.printStackTrace();
		}
		this.productServ.save(product);
	}
	

	@GetMapping(value = "/{productName}/image")
	public ResponseEntity<byte[]> getImage(@PathVariable String productName)throws IOException {
		Product p = this.productServ.findOne(productName);
		if (p != null) {
			if (!p.isPhotoAvailable()){
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			byte[] bytes = Files.readAllBytes(productServ.getImage(p));
			final HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.IMAGE_JPEG);
			return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
		} else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}
	
	@PostMapping(value = "/{productName}/image")
	public ResponseEntity<byte[]> postImage(@RequestBody MultipartFile file, @PathVariable String productName) throws IOException  {
		if(file == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		Product p = this.productServ.findOne(productName);

		if(p == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		 
		this.productServ.saveImage(file,p);
		byte[] bytes = Files.readAllBytes(productServ.getImage(p));
		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_JPEG);
		return new ResponseEntity<>(bytes, headers, HttpStatus.CREATED);

	}
	
	private Product convertToEntity(ProductDTO dto ) {
		return modelMapper.map(dto, Product.class);
	}
	

}
