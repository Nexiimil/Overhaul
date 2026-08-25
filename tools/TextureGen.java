import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * Draws Overhaul's 16x16 item and block textures.
 *
 * <p>The art is generated rather than hand-drawn so that the whole set stays consistent — same
 * outline treatment, same shading direction, same silhouette weight — and so that adding an item
 * is a few lines here rather than a trip through an image editor. It is placeholder-grade pixel
 * art: readable at inventory size, and easy to replace by dropping real PNGs over the output.
 *
 * <p>Run from the repository root:  java tools/TextureGen.java
 */
public final class TextureGen {
	private static final int SIZE = 16;
	private static final int OUTLINE_DARKEN = 55;

	public static void main(String[] args) throws IOException {
		String root = args.length > 0 ? args[0] : "src/main/resources/assets/overhaul/textures";
		File itemDir = new File(root, "item");
		File blockDir = new File(root, "block");
		itemDir.mkdirs();
		blockDir.mkdirs();

		int count = 0;
		count += writeItems(itemDir);
		count += writeCrops(blockDir);
		count += writeBlocks(blockDir);
		System.out.println("Wrote " + count + " textures under " + root);
	}

	// Items --------------------------------------------------------------------------------------

	private static int writeItems(File dir) throws IOException {
		int count = 0;

		count += write(dir, "tomato", c -> {
			c.disc(8, 9, 5, 0xE0342B);
			c.disc(6, 7, 2, 0xF2564A);
			c.rect(7, 2, 2, 3, 0x3E7A2E);
			c.rect(5, 3, 6, 1, 0x4E9139);
		});

		count += write(dir, "lettuce", c -> {
			c.disc(8, 9, 6, 0x4E9B34);
			c.disc(6, 7, 3, 0x6FBE4A);
			c.disc(11, 9, 3, 0x63B040);
			c.disc(8, 12, 3, 0x5AA33B);
			c.disc(8, 9, 2, 0x8ED96A);
		});

		count += write(dir, "corn", c -> {
			c.leaf(3, 6, -1, 0x4E9B34);
			c.leaf(12, 6, 1, 0x4E9B34);
			c.roundRect(6, 2, 4, 12, 0xF2C63D);
			c.kernels(6, 3, 4, 10, 0xD9A621);
		});

		count += write(dir, "cooked_corn", c -> {
			c.roundRect(5, 2, 6, 11, 0xF7D65A);
			c.kernels(5, 3, 6, 9, 0xE0A82A);
			c.rect(6, 13, 4, 2, 0xC98A3A);
			c.px(7, 4, 0xFFF1B0);
		});

		count += write(dir, "popcorn", c -> {
			c.disc(6, 7, 3, 0xFFF4D2);
			c.disc(10, 6, 3, 0xFDEFC4);
			c.disc(8, 11, 3, 0xF7E7B4);
			c.disc(4, 11, 2, 0xFFF4D2);
			c.disc(12, 11, 2, 0xFDEFC4);
			c.px(6, 6, 0xFFFFFF);
			c.px(10, 5, 0xFFFFFF);
		});

		count += write(dir, "chili_pepper", c -> {
			c.rect(8, 3, 2, 2, 0x3E7A2E);
			c.rect(6, 4, 3, 1, 0x4E9139);
			c.blob(new int[][] {
					{ 7, 5, 3, 2 }, { 6, 7, 4, 2 }, { 6, 9, 4, 2 }, { 7, 11, 3, 2 }, { 8, 13, 2, 1 } }, 0xC42B24);
			c.px(7, 6, 0xE8544A);
			c.px(7, 8, 0xE8544A);
		});

		count += write(dir, "flour", c -> {
			c.blob(new int[][] { { 4, 10, 8, 4 }, { 5, 8, 6, 2 }, { 6, 6, 4, 2 }, { 7, 5, 2, 1 } }, 0xF2EEE2);
			c.rect(6, 9, 2, 1, 0xDCD6C4);
			c.rect(9, 11, 2, 1, 0xDCD6C4);
		});

		count += write(dir, "dough", c -> {
			c.disc(8, 9, 5, 0xE8D9A8);
			c.disc(6, 7, 2, 0xF5EBC6);
			c.px(10, 11, 0xD3C08A);
		});

		count += write(dir, "cheese", c -> {
			c.triangle(3, 12, 13, 12, 8, 3, 0xF0BF3A);
			c.rect(3, 12, 10, 2, 0xD79F24);
			c.px(7, 9, 0xD79F24);
			c.px(10, 11, 0xD79F24);
			c.px(6, 11, 0xD79F24);
		});

		count += write(dir, "pasta", c -> {
			c.disc(8, 9, 6, 0xE8CE8A);
			c.arc(8, 9, 4, 0xD9B762);
			c.arc(8, 9, 2, 0xD9B762);
			c.px(6, 6, 0xF4E4B4);
		});

		count += write(dir, "toast", c -> {
			c.roundRect(3, 4, 10, 9, 0xB5763A);
			c.roundRect(5, 6, 6, 5, 0xE0AE64);
			c.rect(6, 2, 4, 3, 0xB5763A);
		});

		count += write(dir, "tomato_sauce", c -> {
			c.bowl(0xD6D2C8, 0xB0AB9E);
			c.rect(4, 8, 8, 2, 0xC0301F);
			c.px(6, 8, 0xE24A34);
			c.px(9, 9, 0xE24A34);
		});

		count += write(dir, "jam", c -> {
			c.bottle(0x8E1E2C, 0xC7C9CC);
			c.px(7, 9, 0xC03A44);
		});

		count += write(dir, "chocolate", c -> {
			c.roundRect(3, 4, 10, 9, 0x4A2A17);
			c.rect(3, 8, 10, 1, 0x351D0F);
			c.rect(8, 4, 1, 9, 0x351D0F);
			c.rect(4, 5, 3, 2, 0x63391F);
		});

		count += write(dir, "fried_egg", c -> {
			c.blob(new int[][] { { 3, 6, 10, 6 }, { 4, 5, 8, 1 }, { 4, 12, 8, 1 }, { 2, 8, 1, 3 } }, 0xF7F3E7);
			c.disc(8, 8, 3, 0xF2B430);
			c.px(7, 7, 0xFBD97A);
		});

		count += write(dir, "honey_glazed_ham", c -> {
			c.blob(new int[][] { { 4, 5, 9, 8 }, { 5, 4, 7, 1 }, { 5, 13, 7, 1 } }, 0xB25A45);
			c.rect(5, 6, 7, 2, 0xD8853E);
			c.rect(5, 10, 6, 1, 0xD8853E);
			c.rect(2, 8, 3, 2, 0xF0EADA);
			c.px(6, 7, 0xF2B44E);
		});

		count += write(dir, "trail_mix", c -> {
			c.blob(new int[][] { { 3, 9, 10, 5 }, { 4, 7, 8, 2 } }, 0xC49A5E);
			c.px(5, 8, 0x4A2A17);
			c.px(8, 7, 0xC0303C);
			c.px(10, 9, 0x8ED96A);
			c.px(6, 11, 0x4A2A17);
			c.px(11, 11, 0xE8C34A);
			c.px(8, 10, 0xF2EEE2);
		});

		count += write(dir, "tomato_seeds", c -> seeds(c, 0xE0342B));
		count += write(dir, "lettuce_seeds", c -> seeds(c, 0x6FBE4A));
		count += write(dir, "corn_seeds", c -> seeds(c, 0xF2C63D));
		count += write(dir, "chili_pepper_seeds", c -> seeds(c, 0xC42B24));

		count += write(dir, "salad", c -> {
			c.bowl(0xD6D2C8, 0xB0AB9E);
			c.disc(6, 8, 2, 0x5AA33B);
			c.disc(10, 8, 2, 0x6FBE4A);
			c.disc(8, 9, 2, 0x4E9B34);
			c.px(9, 8, 0xE0342B);
			c.px(6, 9, 0xF2C63D);
		});

		count += write(dir, "stew", c -> {
			c.bowl(0xD6D2C8, 0xB0AB9E);
			c.rect(4, 8, 8, 2, 0x7A4A22);
			c.px(6, 8, 0xB25A45);
			c.px(9, 9, 0xB25A45);
			c.px(8, 8, 0xE8A03A);
		});

		count += write(dir, "sandwich", c -> {
			c.roundRect(2, 3, 12, 4, 0xC98A3A);
			c.rect(2, 7, 12, 2, 0x5AA33B);
			c.rect(2, 9, 12, 2, 0xB25A45);
			c.roundRect(2, 11, 12, 3, 0xC98A3A);
			c.px(4, 4, 0xE0AE64);
		});

		count += write(dir, "skewer", c -> {
			c.diagonal(2, 14, 14, 2, 0x9A7B4A);
			c.disc(5, 11, 2, 0xB25A45);
			c.disc(8, 8, 2, 0xF2C63D);
			c.disc(11, 5, 2, 0x5AA33B);
		});

		count += write(dir, "pie", c -> {
			c.disc(8, 9, 6, 0xD9A45E);
			c.disc(8, 9, 4, 0xC0301F);
			c.rect(3, 8, 10, 1, 0xE8C68A);
			c.rect(7, 4, 1, 10, 0xE8C68A);
			c.arc(8, 9, 5, 0xB5763A);
		});

		count += write(dir, "elote", c -> {
			// A cob held on a stick, rolled in cheese and dusted with chilli.
			c.diagonal(3, 15, 6, 12, 0x9A7B4A);
			c.rect(5, 12, 2, 2, 0x9A7B4A);
			c.roundRect(5, 2, 6, 11, 0xF7D65A);
			c.kernels(5, 3, 6, 9, 0xE0A82A);
			c.px(6, 4, 0xFFF6D0);
			c.px(9, 6, 0xFFF6D0);
			c.px(7, 9, 0xFFF6D0);
			c.px(9, 11, 0xFFF6D0);
			c.px(8, 5, 0xC42B24);
			c.px(6, 8, 0xC42B24);
			c.px(9, 3, 0xC42B24);
			c.px(7, 12, 0xC42B24);
		});

		count += writeBackpacks(dir);
		return count;
	}

	private static int writeBlocks(File dir) throws IOException {
		// An empty shelf: the vanilla frame with the books taken out, so a stocked shelf and a bare
		// one are told apart by what the player put there rather than by the block itself.
		return write(dir, "empty_bookshelf", c -> {
			c.rect(0, 0, 16, 16, 0x6A5133);
			c.rect(0, 0, 16, 2, 0x8A6A43);
			c.rect(0, 7, 16, 2, 0x8A6A43);
			c.rect(0, 14, 16, 2, 0x8A6A43);
			c.rect(1, 2, 14, 5, 0x4A3722);
			c.rect(1, 9, 14, 5, 0x4A3722);
			c.rect(1, 6, 14, 1, 0x5B442A);
			c.rect(1, 13, 14, 1, 0x5B442A);
			c.px(3, 1, 0x9C7A4E);
			c.px(11, 8, 0x9C7A4E);
		});
	}

	private static int writeBackpacks(File dir) throws IOException {
		int count = 0;
		count += write(dir, "backpack", c -> backpack(c, 0x7B4A2A, 0x9A6238, 0x5A3520));
		count += write(dir, "copper_backpack", c -> backpack(c, 0x7B4A2A, 0x9A6238, 0xC1663A));
		count += write(dir, "iron_backpack", c -> backpack(c, 0x7B4A2A, 0x9A6238, 0xCFCFCF));
		count += write(dir, "gold_backpack", c -> backpack(c, 0x7B4A2A, 0x9A6238, 0xE8C34A));
		count += write(dir, "diamond_backpack", c -> backpack(c, 0x6E4526, 0x8F5A33, 0x4FD6C8));
		count += write(dir, "netherite_backpack", c -> backpack(c, 0x4A3225, 0x63432E, 0x30272A));

		count += write(dir, "backpack_upgrade_smithing_template", c -> {
			c.rect(2, 2, 12, 12, 0xE6E1D3);
			c.rect(3, 3, 10, 10, 0x3A3A3A);
			c.rect(5, 5, 6, 6, 0xE6E1D3);
			c.rect(6, 6, 4, 4, 0x3A3A3A);
			c.rect(7, 7, 2, 2, 0xC1663A);
			c.px(2, 2, 0x00000000);
			c.px(13, 2, 0x00000000);
			c.px(2, 13, 0x00000000);
			c.px(13, 13, 0x00000000);
		});

		return count;
	}

	private static void backpack(Canvas c, int bodyDark, int bodyLight, int trim) {
		c.roundRect(3, 5, 10, 9, bodyDark);
		c.rect(4, 6, 8, 4, bodyLight);
		c.roundRect(3, 3, 10, 4, bodyDark);
		c.rect(4, 4, 8, 2, bodyLight);
		c.rect(6, 9, 4, 3, trim);
		c.rect(7, 10, 2, 1, shade(trim, 40));
		c.rect(2, 6, 1, 6, shade(bodyDark, -25));
		c.rect(13, 6, 1, 6, shade(bodyDark, -25));
		c.px(5, 4, shade(bodyLight, 35));
	}

	private static void seeds(Canvas c, int tint) {
		int dark = shade(tint, -45);
		c.seed(4, 6, tint, dark);
		c.seed(9, 5, tint, dark);
		c.seed(6, 10, tint, dark);
		c.seed(11, 10, tint, dark);
	}

	// Crops --------------------------------------------------------------------------------------

	private static int writeCrops(File dir) throws IOException {
		int count = 0;
		count += crop(dir, "tomato", 0xE0342B);
		count += crop(dir, "lettuce", 0x8ED96A);
		count += crop(dir, "corn", 0xF2C63D);
		count += crop(dir, "chili_pepper", 0xC42B24);
		return count;
	}

	private static int crop(File dir, String name, int fruit) throws IOException {
		int stem = 0x4E9B34;
		int leaf = 0x63B040;

		write(dir, name + "_stage0", c -> {
			c.rect(4, 13, 1, 2, stem);
			c.rect(11, 13, 1, 2, stem);
			c.px(3, 12, leaf);
			c.px(12, 12, leaf);
		});

		write(dir, name + "_stage1", c -> {
			c.rect(4, 10, 1, 5, stem);
			c.rect(11, 10, 1, 5, stem);
			c.rect(2, 11, 2, 1, leaf);
			c.rect(12, 11, 2, 1, leaf);
			c.rect(5, 9, 1, 1, leaf);
			c.rect(10, 9, 1, 1, leaf);
		});

		write(dir, name + "_stage2", c -> {
			c.rect(4, 6, 1, 9, stem);
			c.rect(11, 6, 1, 9, stem);
			c.rect(2, 8, 2, 1, leaf);
			c.rect(12, 8, 2, 1, leaf);
			c.rect(5, 10, 2, 1, leaf);
			c.rect(9, 10, 2, 1, leaf);
			c.px(4, 5, leaf);
			c.px(11, 5, leaf);
		});

		write(dir, name + "_stage3", c -> {
			c.rect(4, 4, 1, 11, stem);
			c.rect(11, 4, 1, 11, stem);
			c.rect(2, 7, 2, 1, leaf);
			c.rect(12, 7, 2, 1, leaf);
			c.rect(5, 11, 2, 1, leaf);
			c.rect(9, 11, 2, 1, leaf);
			c.disc(4, 8, 2, fruit);
			c.disc(11, 9, 2, fruit);
			c.px(3, 7, shade(fruit, 40));
			c.px(10, 8, shade(fruit, 40));
		});

		return 4;
	}

	// Plumbing -----------------------------------------------------------------------------------

	private static int write(File dir, String name, Consumer<Canvas> painter) throws IOException {
		Canvas canvas = new Canvas();
		painter.accept(canvas);
		canvas.outline();
		ImageIO.write(canvas.image, "png", new File(dir, name + ".png"));
		return 1;
	}

	private static int shade(int rgb, int amount) {
		int r = clamp(((rgb >> 16) & 0xFF) + amount);
		int g = clamp(((rgb >> 8) & 0xFF) + amount);
		int b = clamp((rgb & 0xFF) + amount);
		return (r << 16) | (g << 8) | b;
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

	/** A 16x16 ARGB grid with just enough drawing primitives for pixel-art sized shapes. */
	private static final class Canvas {
		private final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		void px(int x, int y, int rgb) {
			if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
				return;
			}

			image.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb);
		}

		void rect(int x, int y, int w, int h, int rgb) {
			for (int dy = 0; dy < h; dy++) {
				for (int dx = 0; dx < w; dx++) {
					px(x + dx, y + dy, rgb);
				}
			}
		}

		/** A rectangle with its four corner pixels knocked out, which reads as rounded at this size. */
		void roundRect(int x, int y, int w, int h, int rgb) {
			rect(x, y, w, h, rgb);
			clear(x, y);
			clear(x + w - 1, y);
			clear(x, y + h - 1);
			clear(x + w - 1, y + h - 1);
		}

		void disc(int cx, int cy, int r, int rgb) {
			for (int y = cy - r; y <= cy + r; y++) {
				for (int x = cx - r; x <= cx + r; x++) {
					int dx = x - cx;
					int dy = y - cy;

					if (dx * dx + dy * dy <= r * r) {
						px(x, y, rgb);
					}
				}
			}
		}

		/** Just the rim of a circle, used for lattice and swirl detail. */
		void arc(int cx, int cy, int r, int rgb) {
			for (int y = cy - r; y <= cy + r; y++) {
				for (int x = cx - r; x <= cx + r; x++) {
					int dx = x - cx;
					int dy = y - cy;
					int d = dx * dx + dy * dy;

					if (d <= r * r && d > (r - 1) * (r - 1)) {
						px(x, y, rgb);
					}
				}
			}
		}

		void triangle(int x1, int y1, int x2, int y2, int x3, int y3, int rgb) {
			int minX = Math.min(x1, Math.min(x2, x3));
			int maxX = Math.max(x1, Math.max(x2, x3));
			int minY = Math.min(y1, Math.min(y2, y3));
			int maxY = Math.max(y1, Math.max(y2, y3));

			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					if (inTriangle(x, y, x1, y1, x2, y2, x3, y3)) {
						px(x, y, rgb);
					}
				}
			}
		}

		void diagonal(int x1, int y1, int x2, int y2, int rgb) {
			int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));

			for (int i = 0; i <= steps; i++) {
				px(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps, rgb);
			}
		}

		/** Fills a list of {x, y, width, height} boxes, for silhouettes that are not a simple shape. */
		void blob(int[][] boxes, int rgb) {
			for (int[] box : boxes) {
				rect(box[0], box[1], box[2], box[3], rgb);
			}
		}

		void kernels(int x, int y, int w, int h, int rgb) {
			for (int dy = 0; dy < h; dy += 2) {
				for (int dx = (dy / 2) % 2; dx < w; dx += 2) {
					px(x + dx, y + dy, rgb);
				}
			}
		}

		void leaf(int x, int y, int direction, int rgb) {
			for (int i = 0; i < 6; i++) {
				px(x + direction * (i / 3), y + i, rgb);
			}
		}

		void seed(int x, int y, int rgb, int dark) {
			rect(x, y, 2, 3, rgb);
			px(x, y + 2, dark);
			px(x + 1, y, dark);
		}

		void bowl(int rim, int body) {
			rect(3, 7, 10, 2, rim);
			rect(4, 9, 8, 3, body);
			rect(5, 12, 6, 1, body);
			clear(3, 7);
			clear(12, 7);
		}

		void bottle(int fill, int glass) {
			rect(7, 2, 2, 2, glass);
			rect(6, 4, 4, 1, glass);
			roundRect(5, 5, 6, 9, glass);
			rect(6, 8, 4, 5, fill);
		}

		void clear(int x, int y) {
			if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
				image.setRGB(x, y, 0);
			}
		}

		/**
		 * Darkens every pixel that sits on the silhouette edge. One pass over the finished sprite
		 * is what gives the whole set a common look without drawing outlines by hand.
		 */
		void outline() {
			int[] snapshot = new int[SIZE * SIZE];

			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					snapshot[y * SIZE + x] = image.getRGB(x, y);
				}
			}

			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					int argb = snapshot[y * SIZE + x];

					if ((argb >>> 24) == 0) {
						continue;
					}

					if (transparent(snapshot, x - 1, y) || transparent(snapshot, x + 1, y)
							|| transparent(snapshot, x, y - 1) || transparent(snapshot, x, y + 1)) {
						image.setRGB(x, y, 0xFF000000 | shade(argb & 0xFFFFFF, -OUTLINE_DARKEN));
					}
				}
			}
		}

		private static boolean transparent(int[] snapshot, int x, int y) {
			if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
				return true;
			}

			return (snapshot[y * SIZE + x] >>> 24) == 0;
		}

		private static boolean inTriangle(int px, int py, int x1, int y1, int x2, int y2, int x3, int y3) {
			int d1 = sign(px, py, x1, y1, x2, y2);
			int d2 = sign(px, py, x2, y2, x3, y3);
			int d3 = sign(px, py, x3, y3, x1, y1);
			boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
			boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
			return !(negative && positive);
		}

		private static int sign(int px, int py, int x1, int y1, int x2, int y2) {
			return (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
		}
	}
}
