package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dynamotaco.models.UpdateOrderRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.stream.Collectors;

public class UpdateOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            UpdateOrderRequest updateRequest = MAPPER.readValue(request.getBody(), UpdateOrderRequest.class);

            if (updateRequest.getEmail() == null || updateRequest.getOrderId() == null || updateRequest.getStatus() == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing required fields: email, orderId, or status");
            }


            String partitionKey = "CUSTOMER#" + updateRequest.getEmail();
            String sortKey = "ORDER#" + updateRequest.getOrderId();

            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "PK", AttributeValue.builder().s(partitionKey).build(),
                            "SK", AttributeValue.builder().s(sortKey).build()
                    ))
                    .updateExpression("SET #status = :status")
                    .expressionAttributeNames(Map.of("#status", "Status"))
                    .expressionAttributeValues(Map.of(
                            ":status", AttributeValue.builder().s(updateRequest.getStatus().toString()).build()
                    ))
                    .conditionExpression("attribute_exists(PK) AND attribute_exists(SK)")
                    .returnValues(ReturnValue.ALL_NEW)
                    .build();
            context.getLogger().log("Updating order with request: " + updateRequest.toString());
            UpdateItemResponse response = dynamoDb.updateItem(updateItemRequest);
            context.getLogger().log("Updated!");



            context.getLogger().log("Returning ");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Access-Control-Allow-Headers", "*",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "*"
                    ))
                    .withBody(MAPPER.writeValueAsString("success"));

        } catch (ConditionalCheckFailedException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withBody("Order not found");
        } catch (Exception e) {
            context.getLogger().log("Error updating order: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error updating order");
        }
    }

}
