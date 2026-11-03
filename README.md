## CCRA (Commercial Consolidated Revenue Analytics)-Forecasting-POC

Repo for UI and Backend Code for CCAR Forecasting Code

## Overview

This POC provides a unified view of Actuals and Forecast revenue data across Business Unit (BU) and Geography (GEO) hierarchies with a rolling 36-month time horizon.

## Features

View actuals data for last 36 months

View forecasted revenue data for future periods

Hierarchy Navigation

BU hierarchy support

GEO hierarchy support 

Drill down across BU and GEO hierarchies

Aggregate values from leaf to parent levels

Compare Forecast vs Actuals (Forecast Variance)


## View Options

By BU Hierarchy

By GEO Hierarchy


## Key Metrics

Actual

Forecast

Forecast Variance


## High-Level Architecture

Source Systems (Flat Files)
      ↓
 Bronze Layer
      ↓
 Silver Layer
      ↓
 Gold Layer
      ↓
 APIs
      ↓
 CCRA Dashboard
