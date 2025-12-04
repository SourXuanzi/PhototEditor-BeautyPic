package com.sour.photoeditorapp

sealed class MediaType {
    object IMAGE : MediaType()
    object VIDEO : MediaType()
}