package com.ecoachsolutions.ecoachbooks.Core;

import com.ecoachsolutions.ecoachbooks.Models.BookDto;

import java.io.InputStream;

public class DownloadBookData {
    public BookDto dto;
    public InputStream inputStream;
    public Boolean success;
    public long size;
}
