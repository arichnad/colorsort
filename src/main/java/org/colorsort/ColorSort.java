package org.colorsort;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ColorSort {
	private static final int MONKEY_SORT_ITERATIONS = 300000000;
	private static final int THREADS = 16;
	private static final int RED_SHIFT = 16, GREEN_SHIFT = 8, BLUE_SHIFT = 0;
	private static final int FULL_ALPHA = 0xff000000;
	private static final int COLOR_MASK = 0xffffff;
	private int width, height;
	private Pixel[] pixels;
	private BufferedImage image;
	private int[] data;

	private static class Pixel {
		private int distance;
		int originalPosition, newColor, originalColor;
		int red, green, blue;

		public Pixel(int originalPosition, int originalColor) {
			//here newColor is assigned to the position in the file . . . that way we don't get duplicates.
			this.originalPosition = this.newColor = originalPosition;
			this.originalColor = originalColor;
			red = originalColor >> RED_SHIFT & 0xff;
			green = originalColor >> GREEN_SHIFT & 0xff;
			blue = originalColor >> BLUE_SHIFT & 0xff;
			updateDistance();
		}

		public int distance(int newColor) {
			int redDifference = (newColor >> RED_SHIFT & 0xff)-red;
			int greenDifference = (newColor >> GREEN_SHIFT & 0xff)-green;
			int blueDifference = (newColor >> BLUE_SHIFT & 0xff)-blue;

			return redDifference * redDifference +
				blueDifference * blueDifference +
				greenDifference * greenDifference;
		}

		public void updateDistance() {
			distance = distance(newColor);
		}
	}

	private void main(File inputFile, File outputFile) throws IOException {
		readFile(inputFile);

		if(pixels.length != 1<<24) {
			System.err.println("ERROR:  expected 1 pixel per color.  you will not see an expected / balanced output.");
			System.err.println("ERROR:  please give a file with 4096x4096 pixels");
			System.err.println("continuing anyways");
		}

		System.out.println("original distance: " + distance());

		//STEP 1:  SORT BY REDS
		sortByReds();

		System.out.println("after sorting reds, distance: " + distance());

		//STEP 2:  MONKEY SORT.  see if swapping two random pixels helps or not.
		parallelMonkeySort();

		System.out.println("after \"monkey sort\", distance: " + distance());

		writeFile(outputFile);
	}

	private void sortByReds() {
		Arrays.sort(pixels, Comparator.comparing((pixel) -> pixel.originalColor >> RED_SHIFT & 0xff));
		for(int position=0; position<pixels.length; position++) {
			pixels[position].newColor = position;
			pixels[position].updateDistance();
		}
	}

	private void checkSwap(int first, int second) {
		int oldDistance = pixels[first].distance + pixels[second].distance;
		int newDistance = pixels[first].distance(pixels[second].newColor) + pixels[second].distance(pixels[first].newColor);
		if(newDistance < oldDistance) {
			//swap
			int temporaryPixel = pixels[first].newColor;
			synchronized(pixels) {
				oldDistance = pixels[first].distance + pixels[second].distance;
				newDistance = pixels[first].distance(pixels[second].newColor) + pixels[second].distance(pixels[first].newColor);
				if(newDistance < oldDistance) {
					pixels[first].newColor = pixels[second].newColor;
					pixels[second].newColor = temporaryPixel;
					pixels[first].updateDistance();
					pixels[second].updateDistance();
				}
			}
		}
	}

	private void monkeySort() {
		Random random = ThreadLocalRandom.current();
		for(int i=0;i<MONKEY_SORT_ITERATIONS;i++) {
			checkSwap(random.nextInt(pixels.length), random.nextInt(pixels.length));
		}
	}

	private void parallelMonkeySort() {
		List<Thread> threads = new ArrayList<>();
		for(int i=0;i<THREADS;i++) {
			Thread thread = new Thread(this::monkeySort);
			threads.add(thread);
			thread.start();
		}

		for(Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private long distance() {
		long distance = 0;
		for (Pixel pixel : pixels) {
			distance += pixel.distance;
		}
		return distance;
	}

	private void readFile(File inputFile) throws IOException {
		image = ImageIO.read(inputFile);
		width=image.getWidth();
		height=image.getHeight();

		pixels = new Pixel[width*height];

		data = image.getRGB(0, 0, width, height, null, 0, width);
		for(int position=0;position<pixels.length;position++) {
			pixels[position] = new Pixel(position, data[position] & COLOR_MASK);
		}
	}

	private void writeFile(File outputFile) throws IOException {
		for(Pixel pixel : pixels) {
			data[pixel.originalPosition] = pixel.newColor + FULL_ALPHA;
		}
		image.setRGB(0, 0, width, height, data, 0, width);

		ImageIO.write(image, "png", outputFile);
	}

	public static void main(String[] args) {
		if(args.length != 2) {
			System.err.println("expected two files:  input file and output file");
			System.exit(1);
		}
		File inputFile = new File(args[0]);
		if(!inputFile.canRead()) {
			System.err.println("CANNOT READ INPUT FILE");
			System.err.println("expected two files:  input file and output file");
			System.exit(1);
		}

		try {
			new ColorSort().main(inputFile, new File(args[1]));
		}
		catch(IOException e) {
			System.err.println("had trouble reading/writing files: " + e);
			e.printStackTrace();
		}
	}

	/*
	private void test(File inputFile) throws IOException {
		image = ImageIO.read(inputFile);
		width=image.getWidth();
		height=image.getHeight();

		data = image.getRGB(0, 0, width, height, null, 0, width);
		for (int color : data) {
			System.out.println(String.format("%02x%02x%02x",
				color >> RED_SHIFT & 0xff,
				color >> GREEN_SHIFT & 0xff,
				color >> BLUE_SHIFT & 0xff));
		}
	}
	*/
}
