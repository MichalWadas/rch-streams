#!/bin/sh

kubectl -n backbase port-forward "$(kubectl -n backbase get pods| grep 'token-converter' -m1 | cut -d' ' -f1)" 18080:8080 &
kubectl -n backbase port-forward "$(kubectl -n backbase get pods| grep 'user-manager' -m1 | cut -d' ' -f1)" 18081:8080 &
kubectl -n backbase port-forward "$(kubectl -n backbase get pods| grep 'access-control' -m1 | cut -d' ' -f1)" 18082:8080 &
kubectl -n backbase port-forward "$(kubectl -n backbase get pods| grep 'arrangement-manager' -m1 | cut -d' ' -f1)" 18083:8080
kubectl -n backbase port-forward "$(kubectl -n backbase get pods| grep 'payveris-mock' -m1 | cut -d' ' -f1)" 18084:8080