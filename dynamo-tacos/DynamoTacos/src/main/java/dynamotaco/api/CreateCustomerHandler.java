package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dynamotaco.models.Customer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

public class CreateCustomerHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Parse the request body to get the Customer object
            Customer customer = MAPPER.readValue(request.getBody(), Customer.class);

            context.getLogger().log("Raw input: " + customer.toString());

            // Verify the Customer object fields
//            context.getLogger().log("Customer firstName " + customer.getFirstName());
//            context.getLogger().log("Customer lastName " + customer.getLastName());
//            context.getLogger().log("Customer email: " + customer.getEmail());
//            context.getLogger().log("Customer phone number: " + customer.getPhoneNumber());

            String email = customer.getEmail();
            String partitionKey = "CUSTOMER#" + email;

            // Check if the customer already exists
            if (customerExists(partitionKey)) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Customer with email " + email + " already exists.");
            }

            // Create a new customer record
            createCustomerRecord(partitionKey, customer, context);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(Map.of(
                            "Access-Control-Allow-Headers", "*",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "*"
                    ))
                    .withBody("Customer with email " + email + " created successfully.");
        } catch (Exception e) {
            context.getLogger().log("Error creating customer: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error creating customer");
        }
    }

    private boolean customerExists(String partitionKey) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s(partitionKey).build());
        key.put("SK", AttributeValue.builder().s("PROFILE").build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

        return dynamoDb.getItem(request).hasItem();
    }

    private void createCustomerRecord(String partitionKey, Customer customer, Context context) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(partitionKey).build());
        item.put("SK", AttributeValue.builder().s("PROFILE").build());
        item.put("FirstName", AttributeValue.builder().s(customer.getFirstName()).build());
        item.put("LastName", AttributeValue.builder().s(customer.getLastName()).build());
        item.put("Email", AttributeValue.builder().s(customer.getEmail()).build());
        item.put("PhoneNumber", AttributeValue.builder().s(customer.getPhoneNumber()).build());

        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();
        context.getLogger().log("Item contents: " + item.toString());
        dynamoDb.putItem(putRequest);
    }
}
