package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import dynamotaco.models.Customer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

public class CreateCustomerHandler implements RequestHandler<Customer, String> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");

    @Override
    public String handleRequest(Customer customer, Context context) {
        String email = customer.getEmail();
        String partitionKey = "CUSTOMER#" + email;

        // Check if the customer already exists
        if (customerExists(partitionKey)) {
            return "Customer with email " + email + " already exists.";
        }

        // Create a new customer record
        createCustomerRecord(partitionKey, customer);
        return "Customer with email " + email + " created successfully.";
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

    private void createCustomerRecord(String partitionKey, Customer customer) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(partitionKey).build());
        item.put("SK", AttributeValue.builder().s("PROFILE").build());
        item.put("Name", AttributeValue.builder().s(customer.getName()).build());
        item.put("Email", AttributeValue.builder().s(customer.getEmail()).build());
        item.put("PhoneNumber", AttributeValue.builder().s(customer.getPhoneNumber()).build());

        PutItemRequest putRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(putRequest);
    }
}
