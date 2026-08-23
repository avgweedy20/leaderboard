import asyncio
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()

        # Navigate to web app
        await page.goto("http://127.0.0.1:5000")
        await page.wait_for_selector(".navbar")

        # Screenshot leaderboard
        await page.screenshot(path="frontend_leaderboard.png")
        print("Captured leaderboard screenshot.")

        # Click theme toggle
        await page.click("#themeToggleBtn")
        await page.wait_for_timeout(300)
        await page.screenshot(path="frontend_dark_theme.png")
        print("Captured dark theme screenshot.")

        await browser.close()

if __name__ == "__main__":
    asyncio.run(main())
