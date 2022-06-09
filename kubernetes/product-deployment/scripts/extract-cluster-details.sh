HOST_NAME=$(kubectl -n ingress-nginx get svc ingress-nginx-controller -o json | jq .status.loadBalancer.ingress[0].hostname)
echo "$HOST_NAME" | tr -d '"'