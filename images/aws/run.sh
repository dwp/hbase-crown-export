#!/usr/bin/env bash

source ./environment.sh

init
terraform init
terraform apply -auto-approve
add_status_item
add_empty_status_item
add_completed_items
