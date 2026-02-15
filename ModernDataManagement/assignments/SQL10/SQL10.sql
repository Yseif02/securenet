\connect sql10

-- Query 1: Total harvest by crop (Order by ascending crop name, limit 10)
select c.name, coalesce(sum(h.amount), 0) as total
from Crop c
left join Harvest h on c.name = h.crop
group by c.name
order by c.name asc
limit 10;

-- Query 2: Crop with greatest profit per year (Sort year descending, limit 10)
with YearlyProfit as (
    select
        sp.year,
        sp.crop as crop_name,
        (sp.saleprice - c.cost) as revenue
    from SalePrice sp
    JOIN Cost c on sp.crop = c.crop and sp.year = c.year
),
MaxProfitPerYear as (
    select
        year,
        max(revenue) as max_rev
    from YearlyProfit
    group by year
)
select
    yp.year,
    yp.crop_name,
    yp.revenue
from YearlyProfit yp
join MaxProfitPerYear mp on yp.year = mp.year and yp.revenue = mp.max_rev
order by yp.year DESC
limit 10;

-- Query 3: Crops harvested every year since first harvest through 2020 ()
with CropYears as (
    select
        crop as crop_name,
        extract(year from date):: int as year
    from Harvest
    where extract(year from date) <= 2020
),
CropYearRange as (
    select
        crop_name,
        Min(year) as first_year,
        max(year) as last_year,
        count(distinct year) as years_harvested
    from CropYears
    group by crop_name
),
ExpectedYears as (
    select
        crop_name,
        first_year,
        least(last_year, 2020) as end_year,
        (least(last_year, 2020) - first_year + 1) as expected_count,
        years_harvested

    from CropYearRange
)
select crop_name
from ExpectedYears
where years_harvested = expected_count and end_year = 2020
order by crop_name asc
limit 10;

-- Query 4: Crops harvested at least 3 times in Q1 2024
select c.family as crop_family,
       c.name as crop_name
from Crop c
join Harvest h on c.name = h.crop
where h.date between '2024-01-01' AND '2024-03-30'
group by c.family, c.name
having count(*) >= 3
order by c.family asc , c.name asc