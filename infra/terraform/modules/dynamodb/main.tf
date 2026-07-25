resource "aws_dynamodb_table" "audit" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "request_id"

  attribute {
    name = "request_id"
    type = "S"
  }

  tags = {
    Name        = var.table_name
    Environment = var.environment
  }
}
