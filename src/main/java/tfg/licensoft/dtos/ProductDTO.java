package tfg.licensoft.dtos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("Product")
public class ProductDTO {
	
	private Long id;
	private List<LicenseDTO>licenses;

	@ApiModelProperty(example="NameMocked",required=true)
	private String name;
	
	@ApiModelProperty(example="p_id",required=false)
	private String productStripeId;
	
	private HashMap<String,String> plans;
	
	@ApiModelProperty(example="[\r\n" + 
			"            \"M\",\"D\", \"A\"\r\n" + 
			"        ]",required=true)
	private List<String> typeSubs; 
		
	@ApiModelProperty(example="Description Mocked for Swagger test execution",required=true)
	private String description;
	
	@ApiModelProperty(example="www.testSwagger.com",required=true)
	private String webLink;
	
	@ApiModelProperty(example="false",required=true)
	private boolean photoAvailable;
	
	@ApiModelProperty(example="testPath",required=true)
	private String photoSrc;
	
	@ApiModelProperty(example="{\r\n" + 
			"            \"A\": 122.0,\r\n" + 
			"            \"D\": 1.0,\r\n" + 
			"            \"M\": 12.0\r\n" + 
			"        }",required=true)
	private HashMap<String,Double> plansPrices;
	
	@ApiModelProperty(example="skuTest",required=true)
	private String sku;
	
	@ApiModelProperty(example="true",required=true)
	private boolean active;
	
	@ApiModelProperty(example="7",required=true)
	private int trialDays;
	
	@ApiModelProperty(example="Online",required=true)
	private String mode;
	

	

	public ProductDTO() {}
	
	public ProductDTO(String name, String mode) {
		this.name=name;
		this.licenses = new ArrayList<>();
		this.plans = new HashMap<>();
		this.typeSubs= new ArrayList<>();
		this.plansPrices = new HashMap<>();
		this.active=true; 
		this.mode = mode;
		this.photoSrc="";
 
	}
	
	
	
	//Getters & Setters
	
	public String getPhotoSrc() {
		return photoSrc;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public int getTrialDays() {
		return trialDays;
	}

	public void setTrialDays(int trialDays) {
		this.trialDays = trialDays;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

	public void setPhotoSrc(String photoSrc) {
		this.photoSrc = photoSrc;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getWebLink() {
		return webLink;
	}

	public void setWebLink(String webLink) {
		this.webLink = webLink;
	}

	public boolean isPhotoAvailable() {
		return photoAvailable;
	}

	public void setPhotoAvailable(boolean photoAvailable) {
		this.photoAvailable = photoAvailable;
	}

	
	public List<String> getTypeSubs() {
		return typeSubs;
	}

	public void setTypeSubs(List<String> typeSubs) {
		this.typeSubs = typeSubs;
	}

	public String getProductStripeId() {
		return productStripeId;
	}

	public void setProductStripeId(String productStripeId) {
		this.productStripeId = productStripeId;
	}


	public HashMap<String, String> getPlans() {
		return plans;
	}

	public void setPlans(HashMap<String, String> plans) {
		this.plans = plans;
	}

	public List<LicenseDTO> getLicenses() {
		return licenses;
	}

	public void setLicenses(List<LicenseDTO> licenses) {
		this.licenses = licenses;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	
	public HashMap<String, Double> getPlansPrices() {
		return plansPrices;
	}

	public void setPlansPrices(HashMap<String, Double> plansPrices) {
		this.plansPrices = plansPrices;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ProductDTO other = (ProductDTO) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
