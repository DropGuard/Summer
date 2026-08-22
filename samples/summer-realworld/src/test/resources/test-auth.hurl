# Register
POST http://localhost:8080/api/users
{
  "user": {
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }
}
HTTP 200
[Asserts]
jsonpath "$.user.username" == "testuser"
jsonpath "$.user.email" == "test@example.com"
jsonpath "$.user.token" isString
jsonpath "$.user.token" not isEmpty
[Captures]
token: jsonpath "$.user.token"

# Login
POST http://localhost:8080/api/users/login
{
  "user": {
    "email": "test@example.com",
    "password": "password123"
  }
}
HTTP 200
[Asserts]
jsonpath "$.user.username" == "testuser"
jsonpath "$.user.email" == "test@example.com"
jsonpath "$.user.token" isString
jsonpath "$.user.token" not isEmpty

# Get current user
GET http://localhost:8080/api/user
Authorization: Token {{token}}
HTTP 200
[Asserts]
jsonpath "$.user.username" == "testuser"
jsonpath "$.user.email" == "test@example.com"