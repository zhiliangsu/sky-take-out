package com.sky.test;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;

public class POITest {
    public static void read() throws IOException {
        // 通过输入流读取指定的文件
        FileInputStream in = new FileInputStream(new File("test.xlsx"));
        XSSFWorkbook excel = new XSSFWorkbook(in);

        // 获取Excel文件的第1个sheet页
        XSSFSheet sheet = excel.getSheetAt(0);

        // 获取sheet页的最后一行的行号
        int lastRowNum = sheet.getLastRowNum();

        for (int i = 0; i <= lastRowNum; i++) {
            // 获取sheet页中的行
            XSSFRow titleRow = sheet.getRow(i);
            // 获取行的第2个单元格
            XSSFCell cell1 = titleRow.getCell(1);
            // 获取单元格中的文本内容
            String cellValue1 = cell1.getStringCellValue();
            // 获取行的第3个单元格
            XSSFCell cell2 = titleRow.getCell(2);
            // 获取单元格中的文本内容
            String cellValue2 = cell2.getStringCellValue();
            System.out.println(cellValue1 + " " + cellValue2);
        }

        // 关闭资源
        in.close();
        excel.close();
    }

    /**
     * 基于POI向Excel文件写入数据
     *
     * @throws IOException
     */
    public static void write() throws IOException {
        // 在内存中创建一个Excel文件对象
        XSSFWorkbook excel = new XSSFWorkbook();
        // 创建sheet页
        XSSFSheet sheet = excel.createSheet("test");

        // 在sheet页中创建行, 0表示第1行
        XSSFRow row = sheet.createRow(0);
        // 创建单元格并在单元格中设置值, 单元格编号也是从0开始, 1表示第2个单元格
        row.createCell(1).setCellValue("姓名");
        row.createCell(2).setCellValue("城市");

        row = sheet.createRow(1);
        row.createCell(1).setCellValue("张三");
        row.createCell(2).setCellValue("北京");

        row = sheet.createRow(2);
        row.createCell(1).setCellValue("李四");
        row.createCell(2).setCellValue("上海");

        // 通过输出流将内存中的Excel文件写入到磁盘上
        FileOutputStream out = new FileOutputStream(new File("test.xlsx"));
        excel.write(out);

        // 关闭资源
        out.flush();
        out.close();
        excel.close();
    }

    public static void main(String[] args) throws IOException {
        // write();
        read();
    }
}
