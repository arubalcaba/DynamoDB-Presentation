# TacoOrderingApp DynamoDB Table Data Model

## Table Schema

- **Table Name**: TacoOrderingApp
- **Primary Key**:
  - Partition Key: `PK` (String)
  - Sort Key: `SK` (String)

## Attribute Definitions

| Attribute      | Type  | Description                                          |
|----------------|-------|------------------------------------------------------|
| PK             | S     | Partition Key for various entities (e.g., MENU, CUSTOMER) |
| SK             | S     | Sort Key for entities, varies per entity type        |
| Status         | S     | Used for querying orders by status in GSI1           |
| OrderDate      | S     | Used for sorting orders by date in GSI1              |
| CustomerId     | S     | Customer identifier, used in GSI2                    |
| TacoId         | S     | Taco identifier, used in GSI2                        |
| OrderId        | S     | Order identifier, used in GSI3                       |
| SideItemId     | S     | Side item identifier, used in GSI3                   |

## Global Secondary Indexes (GSIs)

1. **GSI1_StatusOrderDate**
   - Partition Key: `Status`
   - Sort Key: `OrderDate`
   - Projection: ALL

2. **GSI2_CustomerIdTacoId**
   - Partition Key: `CustomerId`
   - Sort Key: `TacoId`
   - Projection: ALL

3. **GSI3_OrderIdSideItemId**
   - Partition Key: `OrderId`
   - Sort Key: `SideItemId`
   - Projection: ALL

## Entity Descriptions

- **Customer**:
  - PK: `CUSTOMER#<CustomerId>`
  - SK: `PROFILE`
  - Stores customer details: `CustomerId`, `Name`, `Email`, `PhoneNumber`

- **Order**:
  - PK: `CUSTOMER#<CustomerId>`
  - SK: `ORDER#<OrderId>`
  - Attributes: `OrderId`, `OrderDate`, `TotalPrice`, `Status`

- **Taco**:
  - PK: `ORDER#<OrderId>`
  - SK: `TACO#<TacoId>`
  - Attributes: `TacoId`, `Type`, `BasePrice`

- **Topping**:
  - PK: `TACO#<TacoId>`
  - SK: `TOPPING#<ToppingId>`
  - Attributes: `ToppingId`, `Name`, `Price`

- **Side Item**:
  - PK: `ORDER#<OrderId>`
  - SK: `SIDE#<SideItemId>`
  - Attributes: `SideItemId`, `Name`, `Price`, `Quantity`

## Example Item Structure

### Customer
```json
{
  "PK": "CUSTOMER#12345",
  "SK": "PROFILE",
  "CustomerId": "12345",
  "Name": "John Doe",
  "Email": "john@example.com",
  "PhoneNumber": "123-456-7890"
}
```

### Order
```json
{
  "PK": "CUSTOMER#12345",
  "SK": "ORDER#98765",
  "OrderId": "98765",
  "OrderDate": "2024-10-28",
  "TotalPrice": 15.50,
  "Status": "Pending"
}
```

### Taco
```json
{
  "PK": "ORDER#98765",
  "SK": "TACO#001",
  "TacoId": "001",
  "Type": "Beef Taco",
  "BasePrice": 5.00
}

```

### Topping
```json
{
  "PK": "TACO#001",
  "SK": "TOPPING#01",
  "ToppingId": "01",
  "Name": "Cheese",
  "Price": 0.50
}
```

