curl --header "Content-Type: application/json" \
  --request POST \
  --data '{"username": "joe", "email": "joe@gmail.com", "password": "helloworld", "password2": "helloworld"}' \
  http://localhost:8080/auth/register