package tfg.licensoft.api;

import org.springframework.http.ResponseEntity;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiResponse;

import tfg.licensoft.api.GeneralController.SimpleResponse;

public interface IGeneralController {
	
    @ApiOperation(value = "Returns the Stripe public key needed for the front-end.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Stripe's Public Key"),
            @ApiResponse(code = 404, message = "Stripe's Public Key Not Found")
    })
	ResponseEntity<SimpleResponse> getPublicStripeKey();
    
    
    @ApiOperation(value = "Returns the application-web's name.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Application-web's name"),
            @ApiResponse(code = 404, message = "Application-web's name Not Found")
    })
    ResponseEntity<SimpleResponse> getAppName();
    
    
    @ApiOperation(value = "Returns the application-web's domain where it is deployed.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Application-web's domain"),
            @ApiResponse(code = 404, message = "Application-web's domain Not Found")
    })
	ResponseEntity<SimpleResponse> getAppDomain();
    

}
